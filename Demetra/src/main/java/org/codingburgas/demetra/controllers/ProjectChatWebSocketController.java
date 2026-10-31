package org.codingburgas.demetra.controllers;

import org.codingburgas.demetra.models.Event;
import org.codingburgas.demetra.models.ProjectQuestion;
import org.codingburgas.demetra.models.Role;
import org.codingburgas.demetra.models.User;
import org.codingburgas.demetra.repositories.UserRepository;
import org.codingburgas.demetra.services.EventService;
import org.codingburgas.demetra.services.ProjectQuestionService;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.Map;

@Controller
public class ProjectChatWebSocketController {
    private final EventService eventService;
    private final ProjectQuestionService questionService;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public ProjectChatWebSocketController(EventService eventService, ProjectQuestionService questionService,
                                           UserRepository userRepository, SimpMessagingTemplate messagingTemplate) {
        this.eventService = eventService;
        this.questionService = questionService;
        this.userRepository = userRepository;
        this.messagingTemplate = messagingTemplate;
    }

    @MessageMapping("/events/{eventId}/questions")
    public void question(@DestinationVariable Long eventId, ChatPayload payload, Principal principal) {
        User user = currentUser(principal);
        Event event = eventService.findById(eventId).orElseThrow();
        ProjectQuestion saved = questionService.ask(event, user, payload.text());
        publish(eventId, "QUESTION", saved);
    }

    @MessageMapping("/events/{eventId}/questions/{questionId}/answer")
    public void answer(@DestinationVariable Long eventId, @DestinationVariable Long questionId,
                       ChatPayload payload, Principal principal) {
        User user = currentUser(principal);
        ProjectQuestion saved = questionService.answerForEvent(questionId, eventId, user, payload.text());
        publish(eventId, "ANSWER", saved);
    }

    private User currentUser(Principal principal) {
        return userRepository.findByEmail(principal.getName()).orElseThrow();
    }

    private void publish(Long eventId, String type, ProjectQuestion item) {
        Map<String, Object> payload = Map.of(
                "type", type,
                "id", item.getId(),
                "question", item.getQuestion(),
                "answer", item.getAnswer() == null ? "" : item.getAnswer(),
                "askedBy", item.getAskedBy().getName(),
                "answeredBy", item.getAnsweredBy() == null ? "" : item.getAnsweredBy().getName(),
                "createdAt", item.getCreatedAt().toString(),
                "answeredAt", item.getAnsweredAt() == null ? "" : item.getAnsweredAt().toString()
        );
        messagingTemplate.convertAndSend("/topic/events/" + eventId, (Object) payload);
    }

    public record ChatPayload(String text) { }
}
