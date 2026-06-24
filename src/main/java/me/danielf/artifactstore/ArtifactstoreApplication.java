package me.danielf.artifactstore;

import me.danielf.artifactstore.manifest.ManifestService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class ArtifactstoreApplication {

	private static final Logger log = LoggerFactory.getLogger(ArtifactstoreApplication.class);

	public static void main(String[] args) {
		SpringApplication.run(ArtifactstoreApplication.class, args);
	}

	@Bean
	public CommandLineRunner commandLineRunner(ManifestService manifestService) {
		return args -> {
			manifestService.listAll().forEach(entry -> {
				log.info("repo -> {}:{}", entry.repo(), entry.tag());
			});
		};
	}
}
