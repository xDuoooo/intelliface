package com.xduo.springbootinit.es;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.DeleteRequest;
import co.elastic.clients.elasticsearch.core.GetRequest;
import co.elastic.clients.elasticsearch.core.GetResponse;
import co.elastic.clients.elasticsearch.core.IndexRequest;
import co.elastic.clients.elasticsearch.core.IndexResponse;
import co.elastic.clients.elasticsearch.core.UpdateRequest;
import co.elastic.clients.elasticsearch.indices.DeleteIndexRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
public class ElasticsearchClientTest {

    @Autowired
    private ElasticsearchClient elasticsearchClient;

    private final String INDEX_NAME = "test_index";

    // Index (Create) a document
    @Test
    public void indexDocument() throws Exception {
        Map<String, Object> doc = new HashMap<>();
        doc.put("title", "Elasticsearch Introduction");
        doc.put("content", "Learn Elasticsearch basics and advanced usage.");
        doc.put("tags", "elasticsearch,search");
        doc.put("answer", "Yes");
        doc.put("userId", 1L);
        doc.put("editTime", "2023-09-01 10:00:00");
        doc.put("createTime", "2023-09-01 09:00:00");
        doc.put("updateTime", "2023-09-01 09:10:00");
        doc.put("isDelete", false);

        IndexRequest<Map<String, Object>> request = IndexRequest.of(i -> i
                .index(INDEX_NAME)
                .id("1")
                .document(doc)
        );

        IndexResponse response = elasticsearchClient.index(request);

        assertNotNull(response.id());
    }

    // Get (Retrieve) a document by ID
    @Test
    public void getDocument() throws Exception {
        String documentId = "1";

        GetRequest getRequest = GetRequest.of(g -> g
                .index(INDEX_NAME)
                .id(documentId)
        );

        GetResponse<Map> response = elasticsearchClient.get(getRequest, Map.class);

        assertTrue(response.found());
        assertNotNull(response.source());
        assertEquals("Elasticsearch Introduction", response.source().get("title"));
    }

    // Update a document
    @Test
    public void updateDocument() throws Exception {
        String documentId = "1";

        Map<String, Object> updates = new HashMap<>();
        updates.put("title", "Updated Elasticsearch Title");
        updates.put("updateTime", "2023-09-01 10:30:00");

        UpdateRequest<Map<String, Object>, Map<String, Object>> updateRequest = UpdateRequest.of(u -> u
                .index(INDEX_NAME)
                .id(documentId)
                .doc(updates)
        );

        elasticsearchClient.update(updateRequest, Map.class);

        GetResponse<Map> response = elasticsearchClient.get(g -> g.index(INDEX_NAME).id(documentId), Map.class);
        assertEquals("Updated Elasticsearch Title", response.source().get("title"));
    }

    // Delete a document
    @Test
    public void deleteDocument() throws Exception {
        String documentId = "1";

        DeleteRequest deleteRequest = DeleteRequest.of(d -> d
                .index(INDEX_NAME)
                .id(documentId)
        );

        co.elastic.clients.elasticsearch.core.DeleteResponse result = elasticsearchClient.delete(deleteRequest);
        assertNotNull(result.id());
    }

    // Delete the entire index
    @Test
    public void deleteIndex() throws Exception {
        DeleteIndexRequest deleteIndexRequest = DeleteIndexRequest.of(d -> d
                .index(INDEX_NAME)
        );

        co.elastic.clients.elasticsearch.indices.DeleteIndexResponse deleted = elasticsearchClient.indices().delete(deleteIndexRequest);
        assertTrue(deleted.acknowledged());
    }
}
