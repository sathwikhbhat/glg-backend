package com.golinkgone.glgbackend.entity;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record QrUpsertRequest(@Size(max = 60) String label, @NotNull @Valid QrConfig config) {}