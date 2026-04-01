package org.doubt.doubtpoker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication(scanBasePackages = "org.doubt")
public class DoubtPokerApplication {

	public static void main(String[] args) {
		SpringApplication.run(DoubtPokerApplication.class, args);
	}

}
