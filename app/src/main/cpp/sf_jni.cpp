// Bridge between Kotlin and the bundled Stockfish 11 (GPL-3, see stockfish/COPYING.txt).
// Stockfish keeps its own text protocol (UCI): we swap std::cin / std::cout for in-memory queues,
// run its normal UCI loop on one thread, and Kotlin sends lines in and reads lines out. No process, no pipes, no files.
#include <jni.h>
#include <condition_variable>
#include <deque>
#include <iostream>
#include <mutex>
#include <string>
#include <thread>
#include <atomic>
#include <chrono>

#include "stockfish/bitboard.h"
#include "stockfish/endgame.h"
#include "stockfish/position.h"
#include "stockfish/search.h"
#include "stockfish/thread.h"
#include "stockfish/tt.h"
#include "stockfish/uci.h"
#include "stockfish/syzygy/tbprobe.h"

namespace PSQT { void init(); }

namespace {

class InBuf : public std::streambuf {
public:
    void push(const std::string& line) {
        { std::lock_guard<std::mutex> l(m); q.push_back(line); }
        cv.notify_one();
    }
    void close() { { std::lock_guard<std::mutex> l(m); closed = true; } cv.notify_all(); }
protected:
    int_type underflow() override {
        std::unique_lock<std::mutex> l(m);
        cv.wait(l, [&] { return !q.empty() || closed; });
        if (q.empty()) return traits_type::eof();
        cur = q.front() + "\n";
        q.pop_front();
        setg(&cur[0], &cur[0], &cur[0] + cur.size());
        return traits_type::to_int_type(cur[0]);
    }
private:
    std::mutex m; std::condition_variable cv; std::deque<std::string> q; std::string cur; bool closed = false;
};

class OutBuf : public std::streambuf {
public:
    bool pop(std::string& out, int timeoutMs) {
        std::unique_lock<std::mutex> l(m);
        if (!cv.wait_for(l, std::chrono::milliseconds(timeoutMs), [&] { return !q.empty(); })) return false;
        out = q.front(); q.pop_front();
        return true;
    }
    void clear() { std::lock_guard<std::mutex> l(m); q.clear(); }
protected:
    int_type overflow(int_type c) override {
        if (c == traits_type::eof()) return traits_type::not_eof(c);
        char ch = (char)c;
        if (ch == '\n') {
            { std::lock_guard<std::mutex> l(m); q.push_back(line); if (q.size() > 4000) q.pop_front(); }
            line.clear();
            cv.notify_one();
        } else line.push_back(ch);
        return c;
    }
    std::streamsize xsputn(const char* s, std::streamsize n) override {
        for (std::streamsize i = 0; i < n; i++) overflow(traits_type::to_int_type(s[i]));
        return n;
    }
private:
    std::mutex m; std::condition_variable cv; std::deque<std::string> q; std::string line;
};

// never destroyed: the engine thread may still be waiting when the process ends
InBuf& inBuf = *new InBuf;
OutBuf& outBuf = *new OutBuf;
std::thread loopThread;
std::atomic<bool> started{false};
std::mutex startM;

}  // namespace

extern "C" {

JNIEXPORT jboolean JNICALL Java_app_capri_mobile_StockfishNative_start(JNIEnv*, jclass, jint hashMb, jint threads) {
    std::lock_guard<std::mutex> g(startM);
    if (started) return JNI_TRUE;
    try {
        std::cin.rdbuf(&inBuf);
        std::cout.rdbuf(&outBuf);
        UCI::init(Options);
        PSQT::init();
        Bitboards::init();
        Position::init();
        Bitbases::init();
        Endgames::init();
        Threads.set(Options["Threads"]);
        Search::clear();
        loopThread = std::thread([] {
            char name[] = "stockfish";
            char* argv[] = { name, nullptr };
            UCI::loop(1, argv);
        });
        loopThread.detach();
        started = true;
        inBuf.push("setoption name Hash value " + std::to_string(hashMb < 1 ? 16 : hashMb));
        inBuf.push("setoption name Threads value " + std::to_string(threads < 1 ? 1 : threads));
        inBuf.push("isready");
        return JNI_TRUE;
    } catch (...) {
        return JNI_FALSE;
    }
}

JNIEXPORT void JNICALL Java_app_capri_mobile_StockfishNative_send(JNIEnv* env, jclass, jstring cmd) {
    if (!started || !cmd) return;
    const char* c = env->GetStringUTFChars(cmd, nullptr);
    std::string s(c ? c : "");
    if (c) env->ReleaseStringUTFChars(cmd, c);
    if (s == "quit") return;  // the engine stays alive for the whole run; quitting could not be undone
    inBuf.push(s);
}

JNIEXPORT jstring JNICALL Java_app_capri_mobile_StockfishNative_read(JNIEnv* env, jclass, jint timeoutMs) {
    std::string out;
    if (!outBuf.pop(out, timeoutMs < 0 ? 0 : timeoutMs)) return nullptr;
    return env->NewStringUTF(out.c_str());
}

JNIEXPORT void JNICALL Java_app_capri_mobile_StockfishNative_drain(JNIEnv*, jclass) { outBuf.clear(); }

}
