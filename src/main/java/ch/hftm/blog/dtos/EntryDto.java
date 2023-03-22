package ch.hftm.blog.dtos;

import java.util.List;
import java.util.stream.Collectors;

import ch.hftm.blog.entities.Entry;
import lombok.Getter;

@Getter
public class EntryDto {
    private String title;
    private String content;
    private List<CommentDto> comments;
    private String author;
    private long likes;
    private boolean likedByMe;
    private boolean approved;

    public EntryDto(Entry entry, String actualUserId) {
        this.title = entry.title;
        this.content = entry.content;
        this.author = entry.autor;
        this.likes = entry.userIdLikes.size();
        this.comments = entry.comments.stream().map(c -> new CommentDto(c)).collect(Collectors.toList());
        if (actualUserId != null) {
            this.likedByMe = entry.userIdLikes.contains(actualUserId);
        }
        this.approved = entry.approved;
    }
}