package com.example.admission.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.admission.auth.entity.UserAccount;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * {@link UserAccount} 的 MyBatis-Plus Mapper 接口。
 */
@Mapper
public interface UserAccountMapper extends BaseMapper<UserAccount> {

    /**
     * 通过手机号 HMAC-SHA256 哈希查找用户账号。
     */
    UserAccount selectByMobileHash(@Param("hash") String hash);

    /**
     * 通过用户名查找用户账号。
     */
    UserAccount selectByUsername(@Param("username") String username);
}
