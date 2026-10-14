package com.lamorim.hubdeia.domain.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import com.lamorim.hubdeia.domain.model.Artifact;
import com.lamorim.hubdeia.domain.model.ArtifactStatus;

public interface ArtifactRepository extends MongoRepository<Artifact, String> {

	Optional<Artifact> findByIdAndConversationId(String id, String conversationId);

	List<Artifact> findByConversationIdOrderByCreatedAtDesc(String conversationId, Pageable page);

	Optional<Artifact> findFirstByConversationIdAndTypeOrderByVersionDesc(String conversationId, String type);

	Optional<Artifact> findFirstByConversationIdAndTypeAndStatusOrderByVersionDesc(String conversationId, String type, ArtifactStatus status);
}
