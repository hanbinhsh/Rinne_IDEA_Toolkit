package com.github.hanbinhsh.rinneideatoolkit.services

import com.intellij.util.messages.Topic
import com.github.hanbinhsh.rinneideatoolkit.model.GraphViewState

fun interface GraphUpdateListener {
    fun graphUpdated(state: GraphViewState)
}

object GraphUpdateTopic {
    val TOPIC: Topic<GraphUpdateListener> = Topic.create(
        "method-call-graph-updates",
        GraphUpdateListener::class.java,
    )
}
