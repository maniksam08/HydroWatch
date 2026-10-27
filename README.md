# HydroWatch

A water-quality monitoring system that ingests water readings (pH, temperature, turbidity), detects anomalies in real time, alerts the admin, and lets them ask an AI assistant what the readings mean and what to do about them.

Built with **Java, Spring, Redis Streams, WebSockets and Docker**.

## Overview

HydroWatch is a small distributed system made of three independent services that talk to each other through Redis Streams. Instead of live hardware sensors, it replays a downloaded **Brisbane River water-quality dataset**, so the full pipeline can be run and demoed anywhere.

Readings flow through the system like this:

```
Brisbane River dataset
        |
        v
sensor-data-collector  --->  data-anomaly-detector  --->  real-time-alert
      (Redis Streams)             (Redis Streams)               |
                                                                v
                                              Dashboard (HTML/CSS/JS) + RAG chatbot
                                                       (WebSockets)
```

## Services

| Service | Responsibility |
|---|---|
| **sensor-data-collector** | Reads the water-quality dataset, publishes readings to Redis Streams, and stores incoming data in Redis. |
| **data-anomaly-detector** | Consumes readings and checks pH, temperature and turbidity against safe ranges. An alert is raised only after **3 consecutive bad readings**, which avoids false alarms from one-off spikes. |
| **real-time-alert** | Receives anomaly events, generates alerts, and pushes them to the dashboard over WebSockets. |

## Features

- **Streaming pipeline:** services are decoupled and communicate through Redis Streams.
- **Consecutive-breach alerting:** alerts fire only after 3 bad readings in a row.
- **Real-time dashboard:** a simple HTML/CSS/JS dashboard receives alerts instantly via WebSockets.
- **RAG chatbot:** built with Spring AI and the Gemini chat and embedding models. SOP documents (`resources/docs/*.txt`) are embedded into a vector store (`SimpleVectorStore`), so users can ask about readings and alerts and get answers grounded in those documents.
- **One-command setup:** everything runs with Docker Compose.

## Tech Stack

- **Language / framework:** Java, Spring Boot
- **Messaging and storage:** Redis (Streams)
- **Real-time delivery:** WebSockets
- **AI:** Spring AI, Gemini (chat + embeddings), SimpleVectorStore
- **Frontend:** HTML, CSS, JavaScript
- **Infrastructure:** Docker, Docker Compose

## Getting Started

### Prerequisites

- Docker and Docker Compose
- A Gemini API key (for the RAG chatbot)

### Run

```bash
git clone https://github.com/maniksam08/<repo-name>.git
cd <repo-name>

# set your Gemini API key (see the chatbot service's configuration for the exact variable name)
export GEMINI_API_KEY=your_key_here

docker compose up --build
```

This starts all three services together with Redis. Once running, open the dashboard in your browser to watch readings, alerts and the chatbot.

## RAG Chatbot

The chatbot answers questions using the SOP documents placed in `src/main/resources/docs/`. At startup these are embedded with the Gemini embedding model and stored in a vector store. When you ask a question, the most relevant passages are retrieved and passed to the Gemini chat model.

Currently the chatbot provides **context and explanations only** (for example, what a turbidity spike means). It does not take actions yet.

## Roadmap

HydroWatch is under active development. Planned improvements include:

- Alert cooldown and escalation
- Voice-based alert notifications to the operator
- Chatbot actions, not just explanations
- More features and refinements

## Author

**Pratham Arora**: [github.com/maniksam08](https://github.com/maniksam08)
