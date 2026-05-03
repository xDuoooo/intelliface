package com.xduo.springbootinit.model.dto.user;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 合并用户兴趣标签请求
 */
@Data
public class UserInterestTagsMergeRequest implements Serializable {

    /**
     * 待合并的兴趣标签
     */
    private List<String> interestTags;

    private static final long serialVersionUID = 1L;
}
