package com.lamorim.hubdeia.domain.repository;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.lamorim.hubdeia.domain.model.Tool;

public interface ToolRepository extends MongoRepository<Tool, String> {
	List<Tool> findByIdInAndEnabledTrue(List<String> ids);
}
