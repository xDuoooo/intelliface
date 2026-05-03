package com.xduo.springbootinit.model.dto.postcomment;

import com.xduo.springbootinit.common.PageRequest;
import java.io.Serializable;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 帖子评论分页请求
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PostCommentQueryRequest extends PageRequest implements Serializable {

    private Long postId;

    /**
     * 排序字段：createTime（最新） | likeNum（最热）
     */
    private String sortField;

    /**
     * 排序方式：descend / ascend
     */
    private String sortOrder;

    private static final long serialVersionUID = 1L;
}
