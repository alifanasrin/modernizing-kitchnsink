package com.demo.notification.repository;

import com.demo.notification.model.NotificationLog;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface NotificationLogRepository extends MongoRepository<NotificationLog, String> {

    Optional<NotificationLog> findBySourceAndEventId(String source, String eventId);
}
