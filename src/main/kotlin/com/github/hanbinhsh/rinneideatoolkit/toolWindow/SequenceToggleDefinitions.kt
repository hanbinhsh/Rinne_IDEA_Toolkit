package com.github.hanbinhsh.rinneideatoolkit.toolWindow

import com.github.hanbinhsh.rinneideatoolkit.model.SequenceToolbarToggleId

internal data class SequenceToolbarToggleDefinition(
    val id: SequenceToolbarToggleId,
    val messageKey: String,
)

internal val SEQUENCE_TOOLBAR_TOGGLE_DEFINITIONS = listOf(
    SequenceToolbarToggleDefinition(
        SequenceToolbarToggleId.SHOW_ACCESSOR_METHODS,
        "toolWindow.showAccessorMethods",
    ),
    SequenceToolbarToggleDefinition(
        SequenceToolbarToggleId.SHOW_PRIVATE_METHODS,
        "toolWindow.showPrivateMethods",
    ),
    SequenceToolbarToggleDefinition(
        SequenceToolbarToggleId.SHOW_MAPPER_TABLES,
        "toolWindow.showMapperTables",
    ),
    SequenceToolbarToggleDefinition(
        SequenceToolbarToggleId.WHITE_BACKGROUND_FOR_EXPORT,
        "toolWindow.whiteBackground",
    ),
    SequenceToolbarToggleDefinition(
        SequenceToolbarToggleId.SHOW_RETURN_MESSAGES,
        "sequence.showReturnMessages",
    ),
    SequenceToolbarToggleDefinition(
        SequenceToolbarToggleId.SHOW_ACTIVATION_BARS,
        "sequence.showActivationBars",
    ),
    SequenceToolbarToggleDefinition(
        SequenceToolbarToggleId.SHOW_CREATE_MESSAGES,
        "sequence.showCreateMessages",
    ),
)
