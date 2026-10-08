package com.momna.modules.flow.bootstrap;

import com.momna.modules.flow.infrastructure.*;
import java.time.Instant;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class StandardFlowDefinitionBootstrap implements ApplicationRunner {
    private final FlowDefinitionRepository definitions;

    public StandardFlowDefinitionBootstrap(FlowDefinitionRepository definitions) {
        this.definitions = definitions;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        for (var spec : StandardFlowDefinitionCatalog.all()) {
            var id = new FlowDefinitionId(spec.key(), spec.version());
            if (definitions.existsById(id)) continue;

            definitions.save(new FlowDefinitionEntity(
                spec.key(),
                spec.version(),
                spec.type(),
                null,
                spec.period(),
                null,
                spec.definitionJson(),
                1,
                Instant.parse("2026-10-07T00:00:00Z")
            ));
        }
    }
}
