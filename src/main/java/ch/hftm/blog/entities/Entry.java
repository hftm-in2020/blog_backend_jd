package ch.hftm.blog.entities;

import java.util.List;
import java.util.Set;

import javax.persistence.CascadeType;
import javax.persistence.CollectionTable;
import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.OneToMany;
import javax.validation.constraints.Size;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import io.smallrye.common.constraint.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;

@NoArgsConstructor @AllArgsConstructor
@Builder
@Entity
public class Entry extends PanacheEntityBase {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    public boolean approved;
    
    @Size(max= 200)
    public String title;

    @Size(max= 10000)
    @Column(length = 10000)
    public String content;

    @OneToMany(cascade = CascadeType.ALL)
    @JoinColumn(name = "entry_id")
    public List<Comment> comments;

    @NotNull
    public String autor;

    @ElementCollection
    @CollectionTable(name = "UserLikes", joinColumns = @JoinColumn(name = "entry_id"))
    @Column(name="userId")
    public Set<String> userIdLikes;
}