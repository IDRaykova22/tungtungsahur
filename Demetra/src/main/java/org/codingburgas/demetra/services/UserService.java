package org.codingburgas.demetra.services;

import org.codingburgas.demetra.models.Role;
import org.codingburgas.demetra.models.User;
import org.codingburgas.demetra.repositories.UserRepository;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

@Service
public class UserService implements UserDetailsService {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    /** Digits, spaces, dashes and brackets, with an optional leading "+". */
    private static final Pattern PHONE_PATTERN = Pattern.compile("^\\+?[0-9 ()-]+$");
    /** Not a backslash: MySQL treats "\" inside string literals as an escape, which breaks ESCAPE '\'. */
    private static final char LIKE_ESCAPE = '!';

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final ObjectProvider<FindByIndexNameSessionRepository<?>> sessions;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder, Clock clock,
                       ObjectProvider<FindByIndexNameSessionRepository<?>> sessions) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.sessions = sessions;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email.trim().toLowerCase())
                .orElseThrow(() -> new UsernameNotFoundException("No account found for " + email));
        return new AppUserPrincipal(user);
    }

    /** The public directory lists active accounts only. */
    @Transactional(readOnly = true)
    public List<User> listRegisteredUsers(String sortBy) {
        Sort.Order nameOrder = Sort.Order.asc("name").ignoreCase();
        if ("date".equals(sortBy)) {
            return userRepository.findByDeactivatedAtIsNull(Sort.by(Sort.Order.asc("createdAt"), nameOrder));
        }
        return userRepository.findByDeactivatedAtIsNull(Sort.by(nameOrder, Sort.Order.asc("createdAt")));
    }

    /**
     * Administration search, filtered, sorted and paged in the database. Every word of {@code query} must appear in
     * the name, email or phone (case-insensitive); a phone-like word also matches the phone without its spaces,
     * dashes and brackets. {@code status} is "active", "inactive" or anything else for all; {@code role} may be null.
     */
    @Transactional(readOnly = true)
    public Page<User> searchForAdministration(String query, String status, Role role, Pageable pageable) {
        List<Specification<User>> filters = new ArrayList<>();
        for (String term : query == null || query.isBlank() ? new String[0] : query.trim().split("\\s+")) {
            filters.add(matchesTerm(term));
        }
        if ("active".equals(status)) {
            filters.add((root, criteria, cb) -> cb.isNull(root.get("deactivatedAt")));
        } else if ("inactive".equals(status)) {
            filters.add((root, criteria, cb) -> cb.isNotNull(root.get("deactivatedAt")));
        }
        if (role != null) {
            filters.add((root, criteria, cb) -> cb.equal(root.get("role"), role));
        }
        return userRepository.findAll(Specification.allOf(filters), pageable);
    }

    private static Specification<User> matchesTerm(String term) {
        String pattern = "%" + escapeLike(term.toLowerCase(Locale.ROOT)) + "%";
        String digits = term.replaceAll("[^0-9]", "");
        boolean phoneLike = digits.length() >= 3 && term.matches("\\+?[0-9 ()-]+");
        return (root, criteria, cb) -> {
            List<Predicate> any = new ArrayList<>();
            any.add(cb.like(cb.lower(root.get("name")), pattern, LIKE_ESCAPE));
            any.add(cb.like(cb.lower(root.get("email")), pattern, LIKE_ESCAPE));
            any.add(cb.like(cb.lower(root.get("phone")), pattern, LIKE_ESCAPE));
            if (phoneLike) {
                Expression<String> phoneDigits = root.get("phone");
                for (String separator : List.of(" ", "-", "(", ")", "+")) {
                    phoneDigits = cb.function("replace", String.class, phoneDigits, cb.literal(separator), cb.literal(""));
                }
                any.add(cb.like(phoneDigits, "%" + digits + "%"));
            }
            return cb.or(any.toArray(Predicate[]::new));
        };
    }

    /** "%" and "_" typed by the admin are literal characters, not wildcards. */
    private static String escapeLike(String value) {
        return value.replace(String.valueOf(LIKE_ESCAPE), "" + LIKE_ESCAPE + LIKE_ESCAPE)
                .replace("%", LIKE_ESCAPE + "%")
                .replace("_", LIKE_ESCAPE + "_");
    }

    @Transactional(readOnly = true)
    public Optional<User> findById(Long id) {
        return userRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email.trim().toLowerCase());
    }

    /** Self-registration from the public form (student or organizer only; validated by the controller). */
    @Transactional
    public User register(String name, String email, String rawPassword, Role role) {
        String normalizedEmail = email.trim().toLowerCase();
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new IllegalArgumentException("An account with this email already exists.");
        }
        User user = new User(normalizedEmail, passwordEncoder.encode(rawPassword), name.trim(), role);
        return userRepository.save(user);
    }

    /** An administrator creates an account with any role, including another administrator. */
    @Transactional
    public User createUser(String name, String email, String phone, String rawPassword, Role role) {
        String normalizedEmail = validEmail(email);
        String validName = validName(name);
        String validPhone = validPhone(phone);
        validatePassword(rawPassword);
        if (role == null) {
            throw new IllegalArgumentException("Choose a role.");
        }
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new IllegalArgumentException("A user with this email already exists.");
        }
        User user = new User(normalizedEmail, passwordEncoder.encode(rawPassword), validName, role);
        user.setPhone(validPhone);
        return userRepository.save(user);
    }

    /**
     * Updates name, email and phone. When someone else's email changes, that user's open sessions end because they
     * are indexed by the old email; a user editing their own profile must refresh their security context instead.
     */
    @Transactional
    public User updateProfile(Long userId, String name, String email, String phone, String actingEmail) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("The user does not exist."));
        String normalizedEmail = validEmail(email);
        String validName = validName(name);
        String validPhone = validPhone(phone);
        if (userRepository.existsByEmailAndIdNot(normalizedEmail, user.getId())) {
            throw new IllegalArgumentException("A user with this email already exists.");
        }
        String previousEmail = user.getEmail();
        user.setName(validName);
        user.setEmail(normalizedEmail);
        user.setPhone(validPhone);
        User saved = userRepository.save(user);
        if (!previousEmail.equals(normalizedEmail) && !previousEmail.equals(actingEmail)) {
            endSessions(previousEmail);
        }
        return saved;
    }

    @Transactional
    public User changeRole(Long userId, Role role, String actingEmail) {
        User user = otherUser(userId, actingEmail, "You cannot change your own role.");
        if (role == null) {
            throw new IllegalArgumentException("Choose a role.");
        }
        if (user.getRole() != role) {
            user.setRole(role);
            userRepository.save(user);
            // New authorities apply from the next sign-in, so end the sessions that carry the old ones.
            endSessions(user.getEmail());
        }
        return user;
    }

    @Transactional
    public User deactivate(Long userId, String actingEmail) {
        User user = otherUser(userId, actingEmail, "You cannot deactivate your own account.");
        if (user.isActive()) {
            user.setDeactivatedAt(LocalDateTime.now(clock));
            userRepository.save(user);
            endSessions(user.getEmail());
        }
        return user;
    }

    @Transactional
    public User activate(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("The user does not exist."));
        user.setDeactivatedAt(null);
        return userRepository.save(user);
    }

    /**
     * Startup sync of the configured administrator: the account with this email is created if missing, otherwise
     * made an active ADMIN, and its password always matches the configured one. Configuration is the source of
     * truth, so the password rules are deliberately not applied here (see {@link #meetsPasswordRules}).
     */
    @Transactional
    public User ensureAdministrator(String name, String email, String rawPassword) {
        if (rawPassword == null || rawPassword.isBlank()) {
            throw new IllegalArgumentException("The administrator password cannot be empty.");
        }
        String normalizedEmail = validEmail(email);
        Optional<User> existing = userRepository.findByEmail(normalizedEmail);
        if (existing.isEmpty()) {
            return userRepository.save(new User(normalizedEmail, passwordEncoder.encode(rawPassword), validName(name),
                    Role.ADMIN));
        }
        User user = existing.get();
        boolean promoted = user.getRole() != Role.ADMIN;
        user.setRole(Role.ADMIN);
        user.setDeactivatedAt(null);
        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            user.setPasswordHash(passwordEncoder.encode(rawPassword));
        }
        User saved = userRepository.save(user);
        if (promoted) {
            endSessions(saved.getEmail());
        }
        return saved;
    }

    public static boolean meetsPasswordRules(String password) {
        try {
            validatePassword(password);
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private User otherUser(Long userId, String actingEmail, String selfMessage) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("The user does not exist."));
        if (user.getEmail().equals(actingEmail)) {
            // Also guarantees that at least one active administrator, the one acting, always remains.
            throw new IllegalArgumentException(selfMessage);
        }
        return user;
    }

    private void endSessions(String email) {
        FindByIndexNameSessionRepository<?> repository = sessions.getIfAvailable();
        if (repository != null) {
            repository.findByPrincipalName(email).keySet().forEach(repository::deleteById);
        }
    }

    private static String validName(String name) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.length() < 2 || trimmed.length() > 80) {
            throw new IllegalArgumentException("The name must be between 2 and 80 characters.");
        }
        return trimmed;
    }

    private static String validEmail(String email) {
        String normalized = email == null ? "" : email.trim().toLowerCase();
        if (normalized.length() > 254 || !EMAIL_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Enter a valid email address.");
        }
        return normalized;
    }

    /** Optional: blank becomes null. Otherwise 6-15 digits (the international maximum), spaces collapsed. */
    private static String validPhone(String phone) {
        String trimmed = phone == null ? "" : phone.trim().replaceAll("\\s+", " ");
        if (trimmed.isEmpty()) {
            return null;
        }
        long digits = trimmed.chars().filter(Character::isDigit).count();
        if (!PHONE_PATTERN.matcher(trimmed).matches() || digits < 6 || digits > 15 || trimmed.length() > 20) {
            throw new IllegalArgumentException("Enter a valid phone number: 6 to 15 digits, optionally starting with a plus sign.");
        }
        return trimmed;
    }

    private static void validatePassword(String password) {
        String value = password == null ? "" : password;
        if (value.length() < 8 || value.length() > 128) {
            throw new IllegalArgumentException("The password must be between 8 and 128 characters.");
        }
        if (value.chars().noneMatch(Character::isLowerCase) || value.chars().noneMatch(Character::isUpperCase)) {
            throw new IllegalArgumentException("The password must contain at least one lowercase and one uppercase letter.");
        }
    }
}
