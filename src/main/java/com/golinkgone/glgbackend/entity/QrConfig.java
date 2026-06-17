package com.golinkgone.glgbackend.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record QrConfig(
        @Min(100) @Max(2000) Integer width,
        @Min(100) @Max(2000) Integer height,
        @Pattern(regexp = SLUG) String shape,
        @Valid QrOptions qrOptions,
        @Valid DotsOptions dotsOptions,
        @Valid CornerOptions cornersSquareOptions,
        @Valid CornerOptions cornersDotOptions,
        @Valid BackgroundOptions backgroundOptions,
        @Valid ImageOptions imageOptions,
        @Valid Border border,
        Boolean useLogo) {

    static final String HEX = "^#([0-9a-fA-F]{6}|[0-9a-fA-F]{8})$";
    static final String SLUG = "^[a-z][a-z-]{0,30}$";
    static final String FONT = "^[a-zA-Z0-9 ,'\"-]{0,60}$";
    static final String WEIGHT = "^(normal|bold|[1-9]00)$";
    static final String STYLE = "^(normal|italic|oblique)$";

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record QrOptions(
            @Min(0) @Max(40) Integer typeNumber, @Pattern(regexp = SLUG) String mode, Ecc errorCorrectionLevel) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DotsOptions(
            @Pattern(regexp = SLUG) String type,
            @Pattern(regexp = HEX) String color,
            @Valid Gradient gradient,
            @DecimalMin("0") @DecimalMax("2") Double size) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CornerOptions(
            @Pattern(regexp = SLUG) String type, @Pattern(regexp = HEX) String color, @Valid Gradient gradient) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BackgroundOptions(
            @Pattern(regexp = HEX) String color,
            @Valid Gradient gradient,
            @DecimalMin("0") @DecimalMax("1") Double round,
            @Min(0) @Max(100) Integer margin) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ImageOptions(
            @Pattern(regexp = SLUG) String mode,
            @DecimalMin("0") @DecimalMax("1") Double imageSize,
            @Min(0) @Max(100) Integer margin,
            @Valid Fill fill) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Fill(@Pattern(regexp = HEX) String color, @Valid Gradient gradient) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Gradient(
            @Pattern(regexp = SLUG) String type,
            @DecimalMin("0") @DecimalMax("6.2832") Double rotation,
            @Valid @Size(min = 2, max = 8) List<ColorStop> colorStops) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ColorStop(@DecimalMin("0") @DecimalMax("1") Double offset, @Pattern(regexp = HEX) String color) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Border(
            @DecimalMin("0") @DecimalMax("200") Double size,
            @Pattern(regexp = HEX) String color,
            @Valid Gradient gradient,
            @DecimalMin("0") @DecimalMax("1") Double round,
            @Size(max = 64) String dasharray,
            @Size(max = 64) String dasharrayOffset,
            @DecimalMin("0") @DecimalMax("200") Double margin,
            Boolean proportional,
            @Valid BorderText text) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BorderText(
            @Pattern(regexp = FONT) String font,
            @Pattern(regexp = HEX) String color,
            @DecimalMin("0") @DecimalMax("200") Double size,
            @Pattern(regexp = WEIGHT) String fontWeight,
            @Pattern(regexp = STYLE) String fontStyle,
            @Valid Edge top,
            @Valid Edge bottom,
            @Valid Edge left,
            @Valid Edge right) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Edge(
            @Size(max = 120) String content,
            @Pattern(regexp = FONT) String font,
            @Pattern(regexp = HEX) String color,
            @DecimalMin("0") @DecimalMax("200") Double size,
            @Pattern(regexp = WEIGHT) String fontWeight,
            @Pattern(regexp = STYLE) String fontStyle) {}

    public enum Ecc {
        L,
        M,
        Q,
        H
    }
}