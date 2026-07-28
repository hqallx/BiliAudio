package com.biliaudio.util

import android.util.Base64
import java.security.KeyFactory
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher

/**
 * bilibili web 密码登录所需的 RSA 加密工具。
 *
 * bilibili 的 /x/passport-login/web/key 接口返回：
 *   - hash: 用于拼接密码的盐值
 *   - key:  X.509 SubjectPublicKeyInfo 的 Base64 字符串（不含 PEM 头尾）
 *
 * 加密流程：将 "hash + 原始密码" 用 RSA/PKCS1Padding 加密，输出 Base64 字符串，
 * 通过 /x/passport-login/web/v6/login 的 password 字段提交。
 */
object RSAUtil {

    fun encryptPassword(rawPassword: String, hash: String, publicKeyBase64: String): String? {
        return try {
            val keyBytes = Base64.decode(publicKeyBase64, Base64.DEFAULT)
            val keySpec = X509EncodedKeySpec(keyBytes)
            val publicKey = KeyFactory.getInstance("RSA").generatePublic(keySpec)

            val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
            cipher.init(Cipher.ENCRYPT_MODE, publicKey)

            val data = (hash + rawPassword).toByteArray(Charsets.UTF_8)
            val encrypted = cipher.doFinal(data)
            Base64.encodeToString(encrypted, Base64.NO_WRAP)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
