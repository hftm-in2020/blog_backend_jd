package ch.hftm.blog.dtos;

import java.time.LocalDateTime;

import ch.hftm.blog.entities.Comment;

public record CommentDto(String comment, LocalDateTime date, String author) {
    CommentDto(Comment c) {
        this(c.comment, c.date, c.userId);
    }
}