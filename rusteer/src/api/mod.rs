//! API clients for Deezer.
//!
//! This module provides two API clients:
//! - [`DeezerApi`]: Public API for querying metadata (no auth required)
//! - [`GatewayApi`]: Gateway API for authenticated operations

pub mod gateway;
pub mod public;

pub use gateway::GatewayApi;
pub use public::DeezerApi;
