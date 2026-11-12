package com.detox.app.ui.launcher

import java.time.LocalDate

/** One line for the home page. author = null for Detox's own lines. */
data class Quote(val text: String, val author: String? = null)

/**
 * Quote of the day. Built into the app (Detox has no internet), so the list is just Kotlin.
 *
 * Most lines are Detox's own. The few quotes WITH an author are from public-domain books
 * (Seneca, Epictetus, Pascal, Thoreau) in old public-domain English translations. Many
 * "famous quotes" online are made up, so we only kept ones we're sure about.
 */
object Quotes {

    val all: List<Quote> = listOf(
        // ---- Detox's own lines ----
        Quote("Put the phone down. The room is still here."),
        Quote("Small steps, every day."),
        Quote("Rest is part of the work."),
        Quote("You don't have to catch up on everything."),
        Quote("Nothing important is happening in the feed."),
        Quote("Look up. Breathe out."),
        Quote("Boredom is where ideas begin."),
        Quote("Sleep is the best update."),
        Quote("One thing at a time."),
        Quote("Your attention is yours. Spend it well."),
        Quote("The scroll never ends. Your evening does."),
        Quote("Quiet minds notice more."),
        Quote("A slow morning is a good morning."),
        Quote("You are allowed to do nothing for a while."),
        Quote("Tomorrow starts with tonight's sleep."),
        Quote("Go for a short walk. The phone can wait."),
        Quote("Notice five things you can see right now."),
        Quote("Drink some water. Stretch your back."),
        Quote("Text a friend instead of scrolling past strangers."),
        Quote("Today, choose one thing that matters."),
        Quote("Let the day be a little empty."),
        Quote("Your best ideas need space."),
        Quote("Finish one small task. Enjoy it."),
        Quote("Attention is a kind of care."),
        Quote("Read a page of a real book."),
        Quote("The light outside changes every minute. Go and see it."),
        Quote("Breathe in for four, out for six."),
        Quote("You already know what you came here to do."),
        Quote("Calm is a skill. Practise it."),
        Quote("Make the next hour count, gently."),
        Quote("It's okay to be unreachable for a while."),
        Quote("Good sleep is a quiet superpower."),
        Quote("Phones are tools, not places to live."),
        Quote("Curiosity grows in quiet."),
        Quote("Let your eyes rest on something far away."),
        Quote("What would make today feel well spent?"),
        Quote("Less input, more thought."),
        Quote("Your future self says thank you."),
        Quote("One deep breath before the next tap."),
        Quote("The best notifications come from real life."),
        Quote("Leave a few minutes empty. It helps."),
        Quote("Write it down, then let it go."),
        Quote("Small habits, big quiet."),
        Quote("Morning light, not screen light."),
        Quote("Night is for sleeping, not for scrolling."),
        Quote("Nothing you miss tonight will matter tomorrow."),
        Quote("A walk clears what the feed clutters."),
        Quote("Wait a moment. Then decide."),
        Quote("You can always check later. Usually you won't need to."),
        Quote("Make something instead of watching something."),
        Quote("Be kind to yourself today."),
        Quote("Your time is the only thing you truly spend."),
        Quote("Ask yourself: is this helping me right now?"),
        Quote("Learn one small thing today."),
        Quote("Leave the phone in another room for an hour."),
        Quote("Real rest has no screen."),
        Quote("Your mind is not a feed. It's a garden."),
        Quote("Plant one good habit and water it daily."),
        Quote("A clear evening makes a clear morning."),
        Quote("Stand up, stretch, look outside."),
        Quote("Call someone you love."),
        Quote("Finish your tea before you open anything."),
        Quote("Feelings pass. Let this one pass too."),
        Quote("Choose depth over noise."),
        Quote("Today is a good day to start again."),
        Quote("Be here for this minute."),
        Quote("Your phone is boring now. Your life doesn't have to be."),
        Quote("Ignore the urge. Follow your plan."),
        Quote("Rest your thumbs. Use your hands."),
        Quote("Tidy one small corner."),
        Quote("Lights down, screens off, sleep well."),
        Quote("The first hour of the day sets the tone. Keep it yours."),
        Quote("A bored brain is a resting brain."),

        // ---- Public-domain quotes (old translations) ----
        Quote("While we are postponing, life speeds by.", "Seneca"),
        Quote("It is not that we have a short space of time, but that we waste much of it.", "Seneca"),
        Quote("First say to yourself what you would be; and then do what you have to do.", "Epictetus"),
        Quote(
            "I have discovered that all the unhappiness of men arises from one single fact, " +
                "that they cannot stay quietly in their own chamber.",
            "Pascal",
        ),
        Quote("Our life is frittered away by detail.", "Thoreau"),
        Quote("Simplicity, simplicity, simplicity!", "Thoreau"),
        Quote("Time is but the stream I go a-fishing in.", "Thoreau"),
    )

    /**
     * Today's quote. Every day gets the next line: the day number (days since 1970) picks it.
     * [offset] = how many times you tapped for "next" today.
     */
    fun forDay(day: LocalDate, offset: Int = 0): Quote =
        all[Math.floorMod(day.toEpochDay() + offset, all.size.toLong()).toInt()]
}
