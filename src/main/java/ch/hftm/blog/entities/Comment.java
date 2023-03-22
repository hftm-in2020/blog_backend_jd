package ch.hftm.blog.entities;

import java.time.LocalDateTime;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.validation.constraints.Size;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;

@NoArgsConstructor @AllArgsConstructor
@Builder
@Entity
public class Comment extends PanacheEntityBase {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    
    public String userId;

    @Size(max= 1000)
    @Column(length = 1000)
    public String comment;

    @Builder.Default
    public LocalDateTime date = LocalDateTime.now();
}