package com.bajaj.quiz.service;

public class ThreadSleeper implements Sleeper {
    @Override
    public void sleepMillis(long millis) throws InterruptedException {
        Thread.sleep(millis);
    }
}
