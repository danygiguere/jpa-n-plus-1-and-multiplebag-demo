package com.nplus1.dto;

import java.util.List;

public record PostDto(Long id, String title, String content, List<ImageDto> images) {}
