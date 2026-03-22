package com.nplus1.dto;

import java.util.List;

public record UserDto(Long id, String fullName, String email, List<PostDto> posts, List<ImageDto> images) {}
