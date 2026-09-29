package com.lamorim.hubdeia.domain.repository;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.lamorim.hubdeia.domain.model.Provider;

public interface ProviderRepository extends MongoRepository<Provider, String> {
}
