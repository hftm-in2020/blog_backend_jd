package ch.hftm.blog.services;

import java.util.List;
import java.util.Set;

import javax.enterprise.event.Observes;
import javax.transaction.Transactional;

import ch.hftm.blog.entities.Comment;
import ch.hftm.blog.entities.Entry;
import io.quarkus.runtime.StartupEvent;

public class ServiceInitialization {

    @Transactional
    public void init(@Observes StartupEvent event) {

        // Ensure that there is some Data around
        if (Entry.count() < 1) {
            var userId1 = "alice";
            var userId2 = "bob";
            var adminId = "admin";
            var commentList = List.of(Comment.builder().comment("Nice one!").userId(userId1).build(),
                    Comment.builder().comment("So true!").userId("bob").build());
            var userLikes = Set.of(userId1, userId2);
            var content = "Lorem ipsum dolor sit amet, consetetur sadipscing elitr, sed diam nonumy eirmod tempor invidunt ut labore et dolore magna aliquyam erat, sed diam voluptua. Lorem ipsum dolor sit amet, consetetur sadipscing elitr, sed diam nonumy eirmod tempor invidunt ut labore et dolore magna aliquyam erat, sed diam voluptua. At vero eos et accusam et justo duo dolores et ea rebum. Stet clita kasd gubergren, ...";
            Entry.builder().title("Java is the one and only programming language").content(content).autor(adminId)
                    .comments(commentList).userIdLikes(userLikes).approved(true).build().persist();

            content = "At vero eos et accusam et justo duo dolores et ea rebum. Stet clita kasd gubergren, no sea takimata sanctus est Lorem ipsum dolor sit amet. Stet clita kasd gubergren, no sea takimata sanctus est Lorem ipsum dolor sit amet. Stet clita kasd gubergren, no sea takimata sanctus est Lorem ipsum dolor sit amet. Stet clita kasd gubergren, no sea takimata sanctus est Lorem ipsum dolor sit amet. Stet clita kasd gubergren, no sea takimata sanctus est Lorem ipsum dolor sit amet. Lorem ipsum dolor sit amet, consetetur sadipscing elitr, sed diam ...";
            Entry.builder().title("Quarkus: With nothing your are more productive!").content(content).autor(userId1)
                    .approved(true).build().persist();

            content = "Stet clita kasd gubergren, no sea takimata sanctus est Lorem ipsum dolor sit amet. Stet clita kasd gubergren, no sea takimata sanctus est Lorem ipsum dolor sit amet. Stet clita kasd gubergren, no sea takimata sanctus est Lorem ipsum dolor sit amet. Stet clita kasd gubergren, no sea takimata sanctus est Lorem ipsum dolor sit amet. Lorem ipsum dolor sit amet, consetetur sadipscing elitr, sed diam ...";
            Entry.builder().title("How to run a Quarkus-app on Azure").content(content).autor(userId1).approved(true)
                    .build()
                    .persist();
        }
    }
}