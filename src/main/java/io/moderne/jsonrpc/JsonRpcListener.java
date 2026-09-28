package io.moderne.jsonrpc;

/**
 * Observes the requests a {@link JsonRpc} sends and handles, for example to time them. It cannot
 * change them: exceptions it throws are ignored. Callbacks run on the threads that send, read, and
 * dispatch messages, so they must return quickly.
 */
public interface JsonRpcListener {

    /**
     * A request this side sent to the peer. Its completion is reported once, when the response
     * arrives or the request otherwise ends.
     */
    Completion requestSent(JsonRpcRequest request);

    /**
     * A request from the peer that this side is about to handle. Its completion is reported once,
     * after the reply has been sent. Requests for a method with no handler are not observed.
     */
    Completion requestReceived(JsonRpcRequest request);

    interface Completion {
        void completed(Outcome outcome);
    }

    enum Outcome {
        SUCCESS,

        /**
         * The peer replied with an error, or for a received request, the handler failed.
         */
        ERROR,

        /**
         * The connection ended before the peer replied.
         */
        CLOSED,

        /**
         * The caller gave up waiting, by completing the response with a
         * {@link java.util.concurrent.TimeoutException}.
         */
        TIMEOUT,

        /**
         * The caller cancelled the response.
         */
        CANCELLED
    }
}
