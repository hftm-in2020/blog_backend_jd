package ch.hftm.blog.services;

import java.util.Optional;

import javax.enterprise.context.ApplicationScoped;
import javax.transaction.Transactional;

import org.eclipse.microprofile.reactive.messaging.Incoming;

import ch.hftm.blog.entities.Entry;
import io.quarkus.logging.Log;

@ApplicationScoped
public class ApprovalReceiver {

    public record ValidationResponse(long id, boolean valid) {
    }

    @Incoming("validation-response")
    @Transactional
    public void sink(ValidationResponse validationResponse) {
        Log.info("Received a validation response: " + validationResponse);
        Optional<Entry> entryOptional = Entry.findByIdOptional(validationResponse.id);
        if (entryOptional.isEmpty()) {
            Log.warn("Entry not found");
            return;
        }

        entryOptional.get().approved = validationResponse.valid;
    }
}
