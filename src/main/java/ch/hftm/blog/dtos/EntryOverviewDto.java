package ch.hftm.blog.dtos;

import ch.hftm.blog.entities.Entry;
import lombok.Getter;

@Getter
public class EntryOverviewDto {
    private final static int PREVIEW_LENGTH = 10;

    private long id;
    private String title;
    private String contentPreview;
    private String author;
    private long likes;
    private long comments;
    private boolean likedByMe;
    private boolean createdByMe;
    private boolean approved;

    public EntryOverviewDto(Entry entry, String actualUserId) {
        this.id = entry.id;
        this.title = entry.title;
        this.author = entry.autor;
        this.likes = entry.userIdLikes.size();
        this.comments = entry.comments.size();
        if (actualUserId != null) {
            this.likedByMe = entry.userIdLikes.contains(actualUserId);
        }
        if (actualUserId != null) {
            this.createdByMe = entry.autor.equals(actualUserId);
        }
        if (entry.content != null && entry.content.length() > PREVIEW_LENGTH) {
            this.contentPreview = entry.content.substring(0, PREVIEW_LENGTH) + "...";
        } else {
            this.contentPreview = entry.content;
        }
        this.approved = entry.approved;
    }
}