package dev.akira.payflow;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class VirtualThreadController {
    
    @GetMapping("/debug/thread")
    public ThreadInfo inspectThread() {
        Thread thread = Thread.currentThread();

        return new ThreadInfo(
            thread.getName(),
            thread.isVirtual()
        ); 
    }

    public record ThreadInfo(
        String threadName,
        boolean virtual
    ) {}
}
