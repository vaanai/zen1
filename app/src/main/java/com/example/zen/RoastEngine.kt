package com.example.zen

import android.content.Context
import android.widget.Toast
import android.os.Handler
import android.os.Looper

object RoastEngine {
    private val roasts = listOf(
        "Oh look, the dopamine goblin is back. Put the damn phone down!",
        "Are you serious? You have the attention span of a wet wipe. Go do something with your life.",
        "Pathetic. Another mindless swipe. Go touch some freaking grass.",
        "Blocked! Do you literally have zero self-control? This is embarrassing.",
        "Nice try, addict. Go read a book or do a push-up. Your brain cells are dying.",
        "Your dopamine-fried brain really tried to swipe again. Get off your ass and get to work.",
        "DMs are for friends. Reels are for brainless zombies. Guess which one you are right now?",
        "Is this really the best use of your life? Get the hell out of here and do something useful.",
        "You scrolled. I blocked. Go cry about it, then go do something productive.",
        "Another attempt? Truly pathetic. Your ancestors did not survive ice ages for you to stare at this crap."
    )

    fun triggerRoast(context: Context) {
        val roast = roasts.random()
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context, roast, Toast.LENGTH_LONG).show()
        }
    }
}
