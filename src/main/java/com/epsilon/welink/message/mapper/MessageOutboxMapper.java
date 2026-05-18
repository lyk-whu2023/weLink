package com.epsilon.welink.message.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.epsilon.welink.message.entity.MessageOutbox;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MessageOutboxMapper extends BaseMapper<MessageOutbox> {
}
