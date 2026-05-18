package com.epsilon.welink.relation.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("group_info")
public class GroupInfo {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String groupName;
    private String avatar;
    private Long ownerId;
    private String notice;
    private Integer memberCount;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
