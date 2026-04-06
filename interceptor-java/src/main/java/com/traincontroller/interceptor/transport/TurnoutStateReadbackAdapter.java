package com.traincontroller.interceptor.transport;

import com.traincontroller.interceptor.persistence.TcCommandEntity;
import java.util.Optional;

public interface TurnoutStateReadbackAdapter {
    Optional<String> readActualState(TcCommandEntity command);
}