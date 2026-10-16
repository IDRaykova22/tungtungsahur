package org.codingburgas.demetra.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.support.GenericConversionService;
import org.springframework.core.serializer.support.DeserializingConverter;
import org.springframework.core.serializer.support.SerializationFailedException;
import org.springframework.core.serializer.support.SerializingConverter;

/**
 * Session attributes are stored in the database as serialized Java objects. After a deployment that changes
 * one of those classes, old rows can no longer be read; by default every request with such a cookie then fails
 * with HTTP 500. Here an unreadable attribute is treated as missing, so the visitor is simply signed out.
 */
@Configuration
public class SessionConfig implements BeanClassLoaderAware {
    private static final Logger log = LoggerFactory.getLogger(SessionConfig.class);

    private ClassLoader classLoader;

    @Override
    public void setBeanClassLoader(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    /** Picked up by Spring Session JDBC by this bean name. */
    @Bean("springSessionConversionService")
    public GenericConversionService springSessionConversionService() {
        GenericConversionService conversionService = new GenericConversionService();
        conversionService.addConverter(Object.class, byte[].class, new SerializingConverter());
        DeserializingConverter deserializer = new DeserializingConverter(classLoader);
        conversionService.addConverter(byte[].class, Object.class, source -> {
            try {
                return deserializer.convert(source);
            } catch (SerializationFailedException ex) {
                log.info("Ignoring a session attribute stored by an older version of the application: {}",
                        ex.getMostSpecificCause().getMessage());
                return null;
            }
        });
        return conversionService;
    }
}
