package com.ironsgrotto.api.model;

import javax.annotation.Nullable;
import lombok.Data;

/** {@code GET /api/plugin/v1/broadcasts}: a chat line for the whole clan. */
@Data
public class Broadcast
{
	/** Plain text. {@code {end}} stands for {@link #eventEndsAt}, in the member's own time. */
	private String message;
	/** ISO-8601 instant. Sent back as {@code seen} once shown. */
	private String sendsAt;
	/** ISO-8601 instant. Null when the broadcast is not about an event. */
	@Nullable
	private String eventEndsAt;
}
