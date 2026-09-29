package com.lamorim.hubdeia.domain.repository;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.lamorim.hubdeia.domain.model.Skill;

public interface SkillRepository extends MongoRepository<Skill, String> {
	List<Skill> findByIdInAndEnabledTrue(List<String> ids);
}
