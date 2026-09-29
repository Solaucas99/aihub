package com.lamorim.hubdeia.domain.repository;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.lamorim.hubdeia.domain.model.Subagent;

public interface SubagentRepository extends MongoRepository<Subagent, String> {
}
