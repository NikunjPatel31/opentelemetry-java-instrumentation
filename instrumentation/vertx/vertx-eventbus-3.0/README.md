# Vert.x EventBus Instrumentation

This module provides instrumentation for the Vert.x EventBus.

## Instrumentation Details

This instrumentation captures the following:

- Sending messages via `send()`, `publish()`, and `request()` methods
- Receiving messages in consumers
- Processing messages by handlers

## Spans Created

The following spans are created:

- `vertx.eventbus.send` - When a message is sent point-to-point
- `vertx.eventbus.publish` - When a message is published to multiple consumers
- `vertx.eventbus.request` - When a request-response message is sent
- `vertx.eventbus.receive` - When a message is received by a consumer
- `vertx.eventbus.handle` - When a message is processed by a handler

## Attributes Added

The following attributes are added to spans:

- `messaging.system`: "vertx-eventbus"
- `messaging.destination`: The address the message is sent to
- `messaging.destination_kind`: "topic"
- `messaging.operation`: The operation type (send, publish, request, receive, process)
- `messaging.message.payload.size`: The size of the message payload (when available)
- `messaging.vertx.eventbus`: "true"

## Context Propagation

This instrumentation propagates context from sender to receiver, allowing distributed tracing across EventBus boundaries.

## Supported Versions

- Vert.x 3.0.0 and later
