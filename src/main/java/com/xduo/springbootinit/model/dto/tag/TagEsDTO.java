package com.xduo.springbootinit.model.dto.tag;

import java.io.Serializable;
import java.util.Date;
import java.util.List;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.DateFormat;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

/**
 * 标签 ES 文档
 */
@Document(indexName = "tag")
@Data
public class TagEsDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 使用规范化标签名作为文档主键，便于增量更新
     */
    @Id
    private String id;

    /**
     * 展示标签名
     */
    @Field(type = FieldType.Keyword)
    private String name;

    /**
     * 规范化标签名（小写）
     */
    @Field(type = FieldType.Keyword)
    private String normalizedName;

    /**
     * 来源场景：question / post / interest
     */
    @Field(type = FieldType.Keyword)
    private List<String> scenes;

    /**
     * 题目标签使用数
     */
    private Integer questionCount;

    /**
     * 帖子标签使用数
     */
    private Integer postCount;

    /**
     * 兴趣标签使用数
     */
    private Integer interestCount;

    /**
     * 标签总使用数
     */
    private Integer useCount;

    /**
     * 更新时间
     */
    @Field(type = FieldType.Date, format = DateFormat.date_time)
    private Date updateTime;
}
