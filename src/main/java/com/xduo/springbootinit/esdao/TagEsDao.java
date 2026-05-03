package com.xduo.springbootinit.esdao;

import com.xduo.springbootinit.model.dto.tag.TagEsDTO;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

/**
 * 标签 ES DAO
 */
public interface TagEsDao extends ElasticsearchRepository<TagEsDTO, String> {
}
