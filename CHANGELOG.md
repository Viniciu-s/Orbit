# Changelog

Todas as mudanças notáveis neste projeto serão documentadas neste arquivo.

O formato é baseado em [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
e este projeto adere ao [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [1.0.1] - 2026-05-13

### 🎉 Primeira Release

**Orbit Event Bus** - Uma biblioteca Java 21 zero-dependency para comunicação orientada a eventos.

### ✨ Features Principais

#### Core Features
- **Event Bus API**: Interface completa para publicação e assinatura de eventos
- **Publicação Síncrona**: `bus.publish(event)` com execução imediata
- **Publicação Assíncrona**: `bus.publishAsync(event)` com execução em thread separada
- **Thread-Safety**: Totalmente seguro para ambientes concorrentes
- **Zero Dependencies**: Apenas SLF4J API (implementação escolhida pelo consumidor)

#### Listeners
- **Lambda Support**: Listeners como expressões lambda ou method references
- **Prioridades**: `Priority.HIGH`, `NORMAL`, `LOW` para controlar ordem de execução
- **One-Shot Listeners**: `subscribeOnce()` para listeners de uso único
- **Async Listeners**: `subscribeAsync()` para execução assíncrona individual
- **Wildcard Listeners**: `subscribeToAll()` para escutar todos os eventos
- **Cancelable Events**: Interface `CancellableEvent` para interromper propagação

#### Annotations
- **@Subscribe**: Registro automático de métodos anotados
- **@Priority**: Controle de prioridade via anotação
- **Auto-Scanning**: `bus.register(handler)` descobre métodos automaticamente
- **Multi-Handler**: Suporte a múltiplos métodos `@Subscribe` na mesma classe

#### Channels
- **Isolated Channels**: `bus.channel("name")` para namespaces isolados
- **Per-Channel Subscription**: Listeners registrados apenas em canais específicos
- **Channel Independence**: Eventos em canais diferentes não se misturam

#### Interceptors
- **Event Pipeline**: `EventInterceptor` para lógica before/after
- **Chain of Responsibility**: Múltiplos interceptors em sequência
- **Use Cases**: Logging, metrics, validation, transformação de eventos

#### Metrics
- **Event Tracking**: Total de eventos publicados
- **Per-Type Metrics**: Contadores por tipo de evento
- **Listener Count**: Número de listeners ativos
- **Processing Time**: Tempo médio e total de processamento
- **API Completa**: `bus.metrics()` retorna `EventBusMetrics`

#### Error Handling
- **Isolated Failures**: Erro em um listener não afeta outros
- **Custom Error Handler**: `bus.setErrorHandler()` para tratamento customizado
- **SLF4J Logging**: Erros logados automaticamente via SLF4J

### 🏗️ Arquitetura

```
io.orbitbus/
├── core/          # Event, EventListener, EventBus, Dispatcher
├── listener/      # ListenerRegistry, AsyncListener, PrioritizedListener
├── annotation/    # @Subscribe, @Priority, AnnotationScanner
├── exception/     # ErrorHandler
├── pipeline/      # EventInterceptor
└── metrics/       # EventBusMetrics, MetricsCollectorInterceptor
```

### 🧪 Qualidade

- **238 testes unitários** (100% passando)
- **Cobertura de código**: > 95%
- **Checkstyle**: 0 violações
- **SpotBugs**: 0 bugs detectados
- **Thread-Safety Tests**: 5 testes avançados de concorrência

### ⚡ Performance

- **Throughput**: > 15.000 eventos/segundo (single-thread)
- **Overhead**: < 0.1ms por evento
- **Memória**: ~10KB para 1.000 listeners

### 📦 Distribuição

- **GroupId**: `io.github.viniciu-s`
- **ArtifactId**: `orbit`
- **Version**: `1.0.0`
- **Java**: 21+
- **Maven Central**: ✅ Disponível

### 📚 Documentação

- README.md completo com Quick Start, Exemplos, Arquitetura e Benchmarks
- PUBLISHING.md com guia detalhado para Maven Central
- Javadoc completo para todas as APIs públicas
- 8 exemplos práticos de uso

### 🤝 Contribuidores

- Vinicius Vieira (@Viniciu-s) - Arquiteto e desenvolvedor principal
