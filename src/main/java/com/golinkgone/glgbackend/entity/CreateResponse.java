package com.golinkgone.glgbackend.entity;

public record CreateResponse(
        String shortUrl,
        byte[] qrCode
) {
}
