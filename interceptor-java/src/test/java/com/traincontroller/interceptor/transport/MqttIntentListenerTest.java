package com.traincontroller.interceptor.transport;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.traincontroller.interceptor.config.InterceptorProperties;
import com.traincontroller.interceptor.model.TurnoutIntent;
import com.traincontroller.interceptor.model.TurnoutState;
import com.traincontroller.interceptor.service.IntentService;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MqttIntentListenerTest {

    @Mock
    private IntentService intentService;

    @Test
    void messageArrivedMapsNativeJmriThrownPayloadToOpenIntent() {
        MqttIntentListener listener = new MqttIntentListener(
                new InterceptorProperties(
                        750,
                        5,
                        500,
                        "/dev/ttyUSB0",
                        19200,
                        "turnout1",
                        new InterceptorProperties.Mqtt("tcp://localhost:1883", "test-client", "/trains/track/turnout/+")
                ),
                intentService,
                new ObjectMapper()
        );

        listener.messageArrived(
                "trains/track/turnout/LT1",
                new MqttMessage("THROWN".getBytes())
        );

        ArgumentCaptor<TurnoutIntent> intentCaptor = ArgumentCaptor.forClass(TurnoutIntent.class);
        verify(intentService).handle(intentCaptor.capture());

        TurnoutIntent intent = intentCaptor.getValue();
        assertNotNull(intent.commandId());
        assertEquals(intent.commandId(), intent.correlationId());
        assertEquals("LT1", intent.turnoutId());
        assertEquals(TurnoutState.OPEN, intent.desiredState());
    }

    @Test
    void messageArrivedPassesThroughNativeJmriTopicTurnoutId() {
        MqttIntentListener listener = new MqttIntentListener(
                new InterceptorProperties(
                        750,
                        5,
                        500,
                        "/dev/ttyUSB0",
                        19200,
                        "turnout1",
                        new InterceptorProperties.Mqtt("tcp://localhost:1883", "test-client", "trains/track/turnout/+")
                ),
                intentService,
                new ObjectMapper()
        );

        listener.messageArrived(
                "trains/track/turnout/turnout1",
                new MqttMessage("THROWN".getBytes())
        );

        ArgumentCaptor<TurnoutIntent> intentCaptor = ArgumentCaptor.forClass(TurnoutIntent.class);
        verify(intentService).handle(intentCaptor.capture());

        TurnoutIntent intent = intentCaptor.getValue();
        assertEquals("turnout1", intent.turnoutId());
        assertEquals(TurnoutState.OPEN, intent.desiredState());
    }

    @Test
    void messageArrivedAcceptsTrainsTopicRoot() {
        MqttIntentListener listener = new MqttIntentListener(
                new InterceptorProperties(
                        750,
                        5,
                        500,
                        "/dev/ttyUSB0",
                        19200,
                        "turnout1",
                        new InterceptorProperties.Mqtt("tcp://localhost:1883", "test-client", "trains/track/turnout/+")
                ),
                intentService,
                new ObjectMapper()
        );

        listener.messageArrived(
                "trains/track/turnout/turnout1",
                new MqttMessage("THROWN".getBytes())
        );

        ArgumentCaptor<TurnoutIntent> intentCaptor = ArgumentCaptor.forClass(TurnoutIntent.class);
        verify(intentService).handle(intentCaptor.capture());

        TurnoutIntent intent = intentCaptor.getValue();
        assertEquals("turnout1", intent.turnoutId());
        assertEquals(TurnoutState.OPEN, intent.desiredState());
    }

    @Test
    void messageArrivedPreservesLegacyJsonIntentPayloads() {
        MqttIntentListener listener = new MqttIntentListener(
                new InterceptorProperties(
                        750,
                        5,
                        500,
                        "/dev/ttyUSB0",
                        19200,
                        "turnout1",
                        new InterceptorProperties.Mqtt("tcp://localhost:1883", "test-client", "trains/track/turnout/+")
                ),
                intentService,
                new ObjectMapper()
        );

        listener.messageArrived(
                "/legacy/topic/turnout/1",
                new MqttMessage("""
                        {
                          \"commandId\": \"cmd-123\",
                          \"correlationId\": \"corr-123\",
                          \"turnoutId\": \"1\",
                          \"desiredState\": \"CLOSED\"
                        }
                        """.getBytes())
        );

        ArgumentCaptor<TurnoutIntent> intentCaptor = ArgumentCaptor.forClass(TurnoutIntent.class);
        verify(intentService).handle(intentCaptor.capture());

        TurnoutIntent intent = intentCaptor.getValue();
        assertEquals("cmd-123", intent.commandId());
        assertEquals("corr-123", intent.correlationId());
        assertEquals("1", intent.turnoutId());
        assertEquals(TurnoutState.CLOSED, intent.desiredState());
    }

    @Test
    void messageArrivedPassesThroughJsonTurnoutId() {
        MqttIntentListener listener = new MqttIntentListener(
                new InterceptorProperties(
                        750,
                        5,
                        500,
                        "/dev/ttyUSB0",
                        19200,
                        "turnout1",
                        new InterceptorProperties.Mqtt("tcp://localhost:1883", "test-client", "trains/track/turnout/+")
                ),
                intentService,
                new ObjectMapper()
        );

        listener.messageArrived(
                                                                "trains/track/turnout/turnout1",
                new MqttMessage("""
                        {
                          \"commandId\": \"cmd-456\",
                          \"turnoutId\": \"turnout1\",
                          \"desiredState\": \"CLOSED\"
                        }
                        """.getBytes())
        );

        ArgumentCaptor<TurnoutIntent> intentCaptor = ArgumentCaptor.forClass(TurnoutIntent.class);
        verify(intentService).handle(intentCaptor.capture());

        TurnoutIntent intent = intentCaptor.getValue();
        assertEquals("cmd-456", intent.commandId());
        assertEquals("cmd-456", intent.correlationId());
        assertEquals("turnout1", intent.turnoutId());
        assertEquals(TurnoutState.CLOSED, intent.desiredState());
    }

    @Test
    void messageArrivedIgnoresUnexpectedPlainTextTopic() {
        MqttIntentListener listener = new MqttIntentListener(
                new InterceptorProperties(
                        750,
                        5,
                        500,
                        "/dev/ttyUSB0",
                        19200,
                        "turnout1",
                        new InterceptorProperties.Mqtt("tcp://localhost:1883", "test-client", "trains/track/turnout/+")
                ),
                intentService,
                new ObjectMapper()
        );

        listener.messageArrived(
                "/trains/track/sensor/LS1",
                new MqttMessage("ACTIVE".getBytes())
        );

        verify(intentService, never()).handle(org.mockito.ArgumentMatchers.any());
    }

        @Test
        void messageArrivedIgnoresTrackRootWhenStrictTrainsRootConfigured() {
                MqttIntentListener listener = new MqttIntentListener(
                                new InterceptorProperties(
                                                750,
                                                5,
                                                500,
                                                "/dev/ttyUSB0",
                                                19200,
                                                "turnout1",
                                                new InterceptorProperties.Mqtt("tcp://localhost:1883", "test-client", "trains/track/turnout/+")
                                ),
                                intentService,
                                new ObjectMapper()
                );

                listener.messageArrived(
                                "track/turnout/001",
                                new MqttMessage("THROWN".getBytes())
                );

                verify(intentService, never()).handle(org.mockito.ArgumentMatchers.any());
        }
}