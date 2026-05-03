package com.xduo.springbootinit.model.vo;

import cn.hutool.json.JSONUtil;
import com.xduo.springbootinit.model.entity.Post;
import java.io.Serializable;
import java.util.Date;
import java.util.List;
import lombok.Data;
import org.springframework.beans.BeanUtils;

/**
 * 帖子视图

 */
@Data
public class PostVO implements Serializable {

    /**
     * id
     */
    private Long id;

    /**
     * 标题
     */
    private String title;

    /**
     * 内容
     */
    private String content;

    /**
     * 发布时 IP 归属地
     */
    private String ipLocation;

    /**
     * 点赞数
     */
    private Integer thumbNum;

    /**
     * 收藏数
     */
    private Integer favourNum;

    /**
     * 举报数
     */
    private Integer reportNum;

    /**
     * 创建用户 id
     */
    private Long userId;

    /**
     * 审核状态
     */
    private Integer reviewStatus;

    /**
     * 审核意见
     */
    private String reviewMessage;

    /**
     * 审核时间
     */
    private Date reviewTime;

    /**
     * 是否置顶
     */
    private Integer isTop;

    /**
     * 是否精选
     */
    private Integer isFeatured;

    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * 更新时间
     */
    private Date updateTime;

    /**
     * 标签列表
     */
    private List<String> tagList;

    /**
     * 创建人信息
     */
    private UserVO user;

    /**
     * 是否已点赞
     */
    private Boolean hasThumb;

    /**
     * 是否已收藏
     */
    private Boolean hasFavour;

    /**
     * 包装类转对象
     *
     * @param postVO
     * @return
     */
    public static Post voToObj(PostVO postVO) {
        if (postVO == null) {
            return null;
        }
        Post post = new Post();
        BeanUtils.copyProperties(postVO, post);
        List<String> tagList = postVO.getTagList();
        post.setTags(JSONUtil.toJsonStr(tagList));
        return post;
    }

    /**
     * 对象转包装类
     *
     * @param post
     * @return
     */
    public static PostVO objToVo(Post post) {
        if (post == null) {
            return null;
        }
        PostVO postVO = new PostVO();
        BeanUtils.copyProperties(post, postVO);
        postVO.setTagList(JSONUtil.toList(post.getTags(), String.class));
        return postVO;
    }
}
