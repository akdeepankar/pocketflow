# Pocketflow Week 2: Infinite Canvas

This week, things got highly visual. I spent my time figuring out how to build the core interface of Pocketflow: the infinite canvas editor. 

Our main goal was simple to state but challenging to build: a smooth workspace where you can slide around, pinch to zoom in and out, draw node cards, and connect them with interactive wires. We wanted to build this entirely in Compose Multiplatform so it looks and runs the same on both iOS and Android. 

But we didn't stop at a single-user canvas. We took it a step further by integrating Supabase Realtime and Presence, enabling multiple users to view and edit the same canvas at the same time, seeing each other's live cursor movements.

---

## Panning and Zooming

In Compose Multiplatform, we control the viewport window using state variables for the current offset (the X and Y pan coordinates) and the scale (the zoom multiplier). 

Compose provides a modifier called `.pointerInput`. Inside it, we use `detectTransformGestures` to catch two-finger zoom gestures and one-finger drag gestures. We apply the zoom and pan adjustments directly to our variables:

```kotlin
// In commonMain/kotlin/app/ak25/pocketflow/ui/editor/CanvasView.kt
var scale by remember { mutableStateOf(1f) }
var offset by remember { mutableStateOf(Offset.Zero) }

Box(
    modifier = Modifier
        .fillMaxSize()
        .pointerInput(Unit) {
            detectTransformGestures { centroid, pan, zoom, rotation ->
                // Adjust zoom factor and keep it within readable limits (0.5x to 2.5x)
                scale = (scale * zoom).coerceIn(0.5f, 2.5f)
                // Add the panning displacement
                offset += pan
            }
        }
) {
    // Child nodes are rendered here, offset and scaled via graphicsLayer
}
```

```
[ User Touch Gesture ]
        │
        ▼
[ detectTransformGestures ] ──► Update scale (0.5x to 2.5x)
        │                   ──► Update offset (X, Y translation)
        ▼
[ Canvas Graphics Layer ] ──► Redraw workspace child nodes
```

---

## Connecting Ports with Curves

When a user drags a line from the output port of one node to the input port of another, we need to draw a flexible wire. 

Using Compose's `Path` drawing functions, we connect the start and end coordinates using a cubic Bézier curve. The math calculates control points to make the line dip and curve naturally, like a physical patch cable:

```kotlin
// Draw cubic connection wire between nodes
fun DrawScope.drawConnection(start: Offset, end: Offset, color: Color) {
    val distance = start.x - end.x
    // Shift control points based on distance to make curves look natural
    val controlPointOffset = (distance / 2).coerceAtLeast(50f)
    
    val path = Path().apply {
        moveTo(start.x, start.y)
        cubicTo(
            start.x + controlPointOffset, start.y, // Control Point 1 (pulls curve right)
            end.x - controlPointOffset, end.y,     // Control Point 2 (pulls curve left)
            end.x, end.y                           // Destination Input Port
        )
    }
    
    drawPath(
        path = path,
        color = color,
        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
    )
}
```

---

## Realtime Collaboration and Presence

To make the canvas a collaborative sandbox, we connected **Supabase Realtime**. When a user moves a node card, we capture the final position and broadcast it to the project's channel. 

Using **Supabase Presence**, we also track active team members. Every time a collaborator moves their finger on the canvas, we send their cursor coordinates to the broadcast channel. The app receives these coordinates and draws a small cursor indicator labeled with the user's name:

```kotlin
// Broadcast local node movement to the team
coroutineScope.launch {
    supabaseChannel.broadcast(
        event = "node-dragged",
        payload = buildJsonObject {
            put("nodeId", node.id)
            put("x", newX)
            put("y", newY)
        }
    )
}
```

```
User A (Drags Node) ──► Broadcast Position ──► Supabase Cloud
                                                      │
                                                      ▼
User B (Sees Update) ◄── Listens to Channel ◄─────────┘
```

This makes the design process incredibly collaborative, since anyone on the team can tweak the AI pipeline layout concurrently.

---

## The Next Step

Now that the canvas handles rendering nodes, drawing connections, and synchronizing collaborative changes in real time, the interface is ready. 

Next week, we are going to write the **Execution Engine** to run the nodes in order, passing data from one node to another and calling our AI APIs!\n