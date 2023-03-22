package ch.hftm.blog.resources;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.annotation.security.RolesAllowed;
import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.transaction.Transactional;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.PATCH;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.SecurityContext;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SecuritySchemeType;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.security.SecurityScheme;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;

import ch.hftm.blog.dtos.CommentNewDto;
import ch.hftm.blog.dtos.EntryDto;
import ch.hftm.blog.dtos.EntryNewDto;
import ch.hftm.blog.dtos.EntryOverviewDto;
import ch.hftm.blog.dtos.LikeInfoDto;
import ch.hftm.blog.entities.Comment;
import ch.hftm.blog.entities.Entry;
import ch.hftm.blog.messages.ValidationRequest;
import io.quarkus.logging.Log;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;

@Path("/entries")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@SecurityScheme(securitySchemeName = "jwt", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "jwt")
@RequestScoped
public class BlogResource {

    @Inject
    SecurityIdentity identity;

    @Inject
    @Channel("validation-request")
    Emitter<ValidationRequest> validationRequEmitter;

    @Tag(name = "01-Blog-Overview")
    @GET
    @SecurityRequirement(name = "jwt", scopes = {})
    @Operation(summary = "List Blog-Entries (only Overview-Information of Entry)", description = "Optional possibility to search with <searchstring> and also with <from> <to>-paging")
    public List<EntryOverviewDto> getEntries(@QueryParam("searchstring") String searchString,
            @QueryParam("from") int from, @QueryParam("to") int to, @Context SecurityContext ctx) {
        List<Entry> entryList;
        if (searchString == null || searchString.isEmpty()) {
            entryList = Entry.listAll();
        } else {
            entryList = Entry.list("title LIKE ?1 or content LIKE ?1", "%" + searchString + "%");
        }

        if (to > 0) {
            entryList = entryList.stream().skip(from-1).limit(to-from+1).collect(Collectors.toList());
        }

        return entryList.stream().map(e -> new EntryOverviewDto((Entry) e, identity.getPrincipal().getName())).collect(Collectors.toList());
    }

    @Tag(name = "02-Blog-Details")
    @GET
    @Path("{id}")
    @SecurityRequirement(name = "jwt", scopes = {})
    @Operation(summary = "Show Details of Blog-Entry")
    public EntryDto getEntry(@PathParam("id") long id, @Context SecurityContext ctx) {
        var actualUser = identity.getPrincipal().getName();
        Optional<Entry> entryOptional = Entry.findByIdOptional(id);
        if (entryOptional.isEmpty()) {
            throw new ResponseException(Status.NOT_FOUND, "Entry with this id is not available!");
        }
        return new EntryDto(entryOptional.get(), actualUser);
    }

    @Tag(name = "02-Blog-Details")
    @PUT
    @Path("{id}/like-info")
    @Transactional
    @SecurityRequirement(name = "jwt", scopes = {})
    @Operation(summary = "Change my Like-Info of a Blog-Entry")
    @Authenticated
    public void changeLikeInfo(@PathParam("id") long id, LikeInfoDto likeInfo, @Context SecurityContext ctx) {
        var actualUser = identity.getPrincipal().getName();
        Optional<Entry> entryOptional = Entry.findByIdOptional(id);
        if (entryOptional.isEmpty()) {
            throw new ResponseException(Status.NOT_FOUND, "Entry with this id is not available!");
        }
        if (likeInfo.likedByMe()) {
            entryOptional.get().userIdLikes.add(actualUser);
        } else {
            entryOptional.get().userIdLikes.remove(actualUser);
        }
    }

    @Tag(name = "02-Blog-Details")
    @POST
    @Path("{id}/comments")
    @Transactional
    @SecurityRequirement(name = "jwt", scopes = {})
    @Operation(summary = "Add Comment")
    @Authenticated
    public void addComment(@PathParam("id") long id, CommentNewDto commentDto, @Context SecurityContext ctx) {
        var actualUser = identity.getPrincipal().getName();
        Optional<Entry> entryOptional = Entry.findByIdOptional(id);
        if (entryOptional.isEmpty()) {
            throw new ResponseException(Status.NOT_FOUND, "Entry with this id is not available!");
        }
        var comment = Comment.builder().comment(commentDto.comment()).userId(actualUser).build();
        entryOptional.get().comments.add(comment);
    }

    @Tag(name = "04-Blog-New Blog")
    @POST
    @SecurityRequirement(name = "jwt", scopes = {})
    @Operation(summary = "Only Users with the user-role are allowed to write new Blog-Posts")
    @RolesAllowed({"user", "admin"})
    public void addEntry(EntryNewDto entryDto, @Context SecurityContext ctx) {
        var actualUser = identity.getPrincipal().getName();
        var entry = persistEntryFromDto(entryDto, actualUser);

        Log.info("Send Validation Request for Blog-Entry with id " + entry.id + " created by user " + actualUser);
        validationRequEmitter.send(new ValidationRequest(entry.id, entry.title +  " "  + entry.content));
    }

    @Transactional
    Entry persistEntryFromDto(EntryNewDto entryDto, String actualUser) {
        var entry = Entry.builder().content(entryDto.content()).title(entryDto.title())
                .autor(actualUser).build();
        entry.persist();
        return entry;
    }

    @Tag(name = "05-Blog-Admin")
    @DELETE
    @Path("{id}")
    @Transactional
    @SecurityRequirement(name = "jwt", scopes = {})
    @Operation(summary = "Delete a Blog-Entry. Users can only delete their own Blogs. Admin can delete all.")
    @RolesAllowed({"user", "admin"})
    public void deleteEntry(@PathParam("id") long id, @Context SecurityContext ctx) {
        Optional<Entry> entryOptional = Entry.findByIdOptional(id);
        if (entryOptional.isEmpty()) {
            throw new ResponseException(Status.NOT_FOUND, "Entry with this id is not available!");
        }
        var actualUser = identity.getPrincipal().getName();
        if (actualUser.equals(entryOptional.get().autor) || identity.getRoles().contains("admin")) {
            entryOptional.get().delete();
        } else {
            throw new ResponseException(Status.FORBIDDEN, "You are only allowed to delete your own posts!");
        }
    }

    @Tag(name = "05-Blog-Admin")
    @PATCH
    @Path("{id}")
    @Transactional
    @SecurityRequirement(name = "jwt", scopes = {})
    @Operation(summary = "Change title/content of a Blog-Entry. Users can only change their own Blogs. Admin can change all.")
    @RolesAllowed({"user", "admin"})
    public void replaceEntry(@PathParam("id") long id, EntryNewDto e, @Context SecurityContext ctx) {
        Optional<Entry> originalEntryOptional = Entry.findByIdOptional(id);
        if (originalEntryOptional.isEmpty()) {
            throw new ResponseException(Status.NOT_FOUND, "Entry with this id is not available!");
        }

        var actualUser = identity.getPrincipal().getName();
        if (actualUser.equals(originalEntryOptional.get().autor)
                || identity.getRoles().contains("admin")) {
            var originalEntry = originalEntryOptional.get();
            if (e.content() != null && !e.content().isEmpty()) {
                originalEntry.content = e.content();
            }
            if (e.title() != null && !e.title().isEmpty()) {
                originalEntry.title = e.title();
            }
        } else {
            throw new ResponseException(Status.FORBIDDEN, "You are only allowed to delete your own posts!");
        }
    }
}