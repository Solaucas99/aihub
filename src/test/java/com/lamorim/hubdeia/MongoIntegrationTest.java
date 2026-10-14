package com.lamorim.hubdeia;

import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;

/**
 * Base for tests that run against the local Mongo container. They use their own database
 * (indexes are created on startup) and start every test with empty collections.
 */
@SpringBootTest(properties = {
		"spring.mongodb.uri=mongodb://admin:admin123@localhost:27017/appdb_test?authSource=admin",
		"hub.bootstrap.enabled=false"
})
public abstract class MongoIntegrationTest {

	@Autowired
	protected MongoTemplate mongo;

	@BeforeEach
	void cleanCollections() {
		mongo.getCollectionNames().forEach(name -> mongo.getCollection(name).deleteMany(new Document()));
	}
}
