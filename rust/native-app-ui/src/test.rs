use base_ui::fs::{self, FsClient, files_dialog::*, unix_path::UnixPath};
use eframe::egui;

struct MyApp<FS> {
  open_dlg: OpenDialog<FS>,
  save_dlg: SaveDialog<FS>,
}

impl<FS: FsClient + Clone + Send + 'static> eframe::App for MyApp<FS> {
  fn ui(&mut self, ui: &mut egui::Ui, _frame: &mut eframe::Frame) {
    egui::CentralPanel::default().show_inside(ui, |ui| {
      //self.folder.show(ui);
      self.save_dlg.show(ui);
    });
  }

  fn logic(&mut self, ctx: &egui::Context, frame: &mut eframe::Frame) {
    egui_extras::install_image_loaders(ctx);
    ///////////
    self.open_dlg.update_egui_context(ctx.clone());
    self.save_dlg.update_egui_context(ctx.clone());
    ///////////
    _ = (ctx, frame);
  }
}

#[cfg(not(target_arch = "wasm32"))]
pub fn main() {
  env_logger::init(); // Log to stderr (if you run with `RUST_LOG=debug`).
  log::info!("start native");

  let rt = tokio::runtime::Runtime::new().unwrap();
  let _ = rt.enter();
  let local = tokio::task::LocalSet::new();
  local.block_on(&rt, async {
    // Настройки нативного окна

    use base_ui::fs::files_dialog::OpenDialog;

    let options = eframe::NativeOptions {
      viewport: egui::ViewportBuilder::default().with_inner_size([400.0, 300.0]), // Размер окна
      ..Default::default()
    };

    let mut fs = fs::fs_ram::FsRamStore::default();

    fs.mkdir_p(UnixPath::new("/dir/subdir")).unwrap();
    for i in 0..10 {
      fs.write(UnixPath::new(format!("/file{i}.txt")), b"file content")
        .unwrap();
    }
    fs.write(UnixPath::new("/file2.txt"), b"file 2 content")
      .unwrap();
    fs.write(UnixPath::new("/dir/file3.txt"), b"file 3 content aaa")
      .unwrap();

    let fs = fs.boxed();
    let open_dialog = OpenDialog::new(fs.clone());
    let save_dialog = SaveDialog::new(fs.boxed());

    let _ = eframe::run_native(
      "My App",
      options,
      Box::new(|_cc| {
        Ok(Box::new(MyApp {
          open_dlg: open_dialog,
          save_dlg: save_dialog,
        }))
      }),
    );
  });
}
