package application.websocket;

import application.User;
import application.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import javax.validation.constraints.NotNull;
import java.io.IOException;

public class GameSocketHandler extends TextWebSocketHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger("application");
    private static final CloseStatus ACCESS_DENIED = new CloseStatus(4500, "Not logged in. Access denied");

    @NotNull
    private final UserService userService;

    @NotNull
    private final GameSocketService gameSocketService;

    @NotNull
    private final MessageHandlerContainer messageHandlerContainer;

    private final ObjectMapper objectMapper;

    public GameSocketHandler(UserService userService,
                             GameSocketService gameSocketService,
                             MessageHandlerContainer messageHandlerContainer,
                             ObjectMapper objectMapper) {
        this.userService = userService;
        this.gameSocketService = gameSocketService;
        this.messageHandlerContainer = messageHandlerContainer;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession webSocketSession) {
        final Long userId = (Long) webSocketSession.getAttributes().get("userId");
        if (userId == null || userService.getUserById(userId) == null) {
            LOGGER.warn("User requested websocket is not registred or not logged in. Openning websocket session is denied.");
            closeSessionSilently(webSocketSession, ACCESS_DENIED);
            return;
        }
        gameSocketService.registerUser(userId, webSocketSession);
    }

    @Override
    protected void handleTextMessage(WebSocketSession webSocketSession, TextMessage message) {
        if (!webSocketSession.isOpen()) {
            return;
        }
        final Long userId = (Long) webSocketSession.getAttributes().get("userId");
        User user;
        if (userId == null || (user = userService.getUserById(userId)) == null) {
            closeSessionSilently(webSocketSession, ACCESS_DENIED);
            return;
        }
        collectMessage(userId, message);
    }

    @SuppressWarnings("OverlyBroadCatchBlock")
    private void collectMessage(Long userId, TextMessage textMessage) {
        final Message message;
        try {
            message = objectMapper.readValue(textMessage.getPayload(), Message.class);
        } catch (IOException e) {
            LOGGER.error("Wrong json format at websocket message ", e);

            //обработать случай, когда получили некорректное сообщение
            //отправить сообщение, чтобы игрок повторил ввод

            return;
        }
        try {
            messageHandlerContainer.handle(message, userId);
        } catch (HandleExeption e) {
            LOGGER.error("Can't handle message of type " + message.getClass().getName() + " with content: " + textMessage, e);

            //обработать случай, когда получили некорректное сообщение
            //отправить сообщение, чтобы игрок повторил ввод

        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession webSocketSession, CloseStatus closeStatus) {
        final Long userId = (Long)webSocketSession.getAttributes().get("userId");
        if (userId == null) {
            LOGGER.warn("User is disconnected but his session was not found (closeStatus = " + closeStatus + ')');
            return;
        }
        gameSocketService.removeUser(userId);
    }

    @Override
    public void handleTransportError(WebSocketSession webSocketSession, Throwable throwable) {
        LOGGER.warn("Websocket transport problem", throwable);
    }

    private void closeSessionSilently(WebSocketSession webSocketSession, CloseStatus closeStatus) {
        final CloseStatus status = (closeStatus == null ? CloseStatus.SERVER_ERROR : closeStatus);
        try {
            webSocketSession.close(status);
        } catch (IOException e) {
            LOGGER.error("Can't close WebSocketSession");
        }
    }

    @Override
    public boolean supportsPartialMessages() {
        return false;
    }
}
