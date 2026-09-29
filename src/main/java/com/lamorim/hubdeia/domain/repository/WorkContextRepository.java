package com.lamorim.hubdeia.domain.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.lamorim.hubdeia.domain.model.WorkContext;

public interface WorkContextRepository extends MongoRepository<WorkContext, String> {
	List<WorkContext> findByEnabledTrue();
	Optional<WorkContext> findByKeyAndEnabledTrue(String key);
}
