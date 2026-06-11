fn main() -> Result<(), Box<dyn std::error::Error>> {
    let proto_file = "proto/jamendo.proto";
    println!("cargo:rerun-if-changed={proto_file}");

    let protoc = protoc_bin_vendored::protoc_bin_path()?;
    unsafe {
        std::env::set_var("PROTOC", protoc);
    }

    tonic_build::configure()
        .build_server(false)
        .compile_protos(&[proto_file], &["proto"])?;

    Ok(())
}
