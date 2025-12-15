package az.ingress.logger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public record ApplicationLogger(Logger logger) {

    public static ApplicationLogger getLogger(Class<?> clazz) {
        var logger = LoggerFactory.getLogger(clazz);
        return new ApplicationLogger(logger);
    }

    public void debug(String message, Object... arguments) {
        logger.debug(message, arguments);
    }

    public void info(String message, Object... arguments) {
        logger.info(message, arguments);
    }

    public void warn(String message, Object... arguments) {
        logger.warn(message, arguments);
    }

    public void error(String message, Object... arguments) {
        logger.error(message, arguments);
    }

    public void trace(String message, Object... arguments) {
        logger.trace(message, arguments);
    }
}