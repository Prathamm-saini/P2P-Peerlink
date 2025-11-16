
"use client";

import { useState } from "react";
import FileUpload from "@/components/FileUpload";
import FileDownload from "@/components/FileDownload";
import InviteCode from "@/components/InviteCode";
import axios from "axios";

export default function Home() {
  const [uploadedFile, setUploadedFile] = useState<File | null>(null);
  const [isUploading, setIsUploading] = useState(false);
  const [isDownloading, setIsDownloading] = useState(false);
  const [port, setPort] = useState<number | null>(null);
  const [activeTab, setActiveTab] = useState<"upload" | "download">("upload");

  // NEW → store original filename returned from backend
  const [uploadedFilename, setUploadedFilename] = useState<string | null>(null);

  const BACKEND_URL = "http://localhost:8080";

  // ---------------------- UPLOAD ------------------------
  const handleFileUpload = async (file: File) => {
    setUploadedFile(file);
    setIsUploading(true);

    try {
      const formData = new FormData();
      formData.append("file", file);

      const response = await axios.post(`${BACKEND_URL}/api/upload`, formData);

      // NEW
      setPort(response.data.code);
      setUploadedFilename(response.data.filename);
    } catch (error) {
      console.error("Error uploading file:", error);
      alert("Upload failed");
    } finally {
      setIsUploading(false);
    }
  };

  // ---------------------- DOWNLOAD ------------------------
  const handleDownload = async (port: number) => {
    setIsDownloading(true);

    try {
      const response = await axios.get(
          `${BACKEND_URL}/api/download?code=${port}`,
          {
            responseType: "blob",
            validateStatus: () => true,
          }
      );

      if (response.status !== 200) {
        alert("Invalid code OR sender closed the link.");
        setIsDownloading(false);
        return;
      }

      // Extract filename from Content-Disposition
      let filename =
          response.headers["content-disposition"]
              ?.split("filename=")[1]
              ?.replace(/"/g, "") || "downloaded-file";

      const contentType =
          response.headers["content-type"] || "application/octet-stream";

      const blob = new Blob([response.data], { type: contentType });
      const url = window.URL.createObjectURL(blob);

      const a = document.createElement("a");
      a.href = url;
      a.download = filename;
      document.body.appendChild(a);
      a.click();
      a.remove();

      window.URL.revokeObjectURL(url);
    } catch (error) {
      console.error("Download error:", error);
      alert("Invalid code OR sender closed the link.");
    } finally {
      setIsDownloading(false);
    }
  };

  return (
      <div className="container mx-auto px-4 py-8 max-w-4xl">
        <header className="text-center mb-12">
          <h1 className="text-4xl font-bold text-blue-600 mb-2">PeerLink</h1>
          <p className="text-xl text-gray-600">Secure P2P File Sharing</p>
        </header>

        <div className="bg-white rounded-lg shadow-lg p-6">
          <div className="flex border-b mb-6">
            <button
                className={`px-4 py-2 font-medium ${
                    activeTab === "upload"
                        ? "text-blue-600 border-b-2 border-blue-600"
                        : "text-gray-500 hover:text-gray-700"
                }`}
                onClick={() => setActiveTab("upload")}
            >
              Share a File
            </button>

            <button
                className={`px-4 py-2 font-medium ${
                    activeTab === "download"
                        ? "text-blue-600 border-b-2 border-blue-600"
                        : "text-gray-500 hover:text-gray-700"
                }`}
                onClick={() => setActiveTab("download")}
            >
              Receive a File
            </button>
          </div>

          {activeTab === "upload" ? (
              <div>
                <FileUpload onFileUpload={handleFileUpload} isUploading={isUploading} />

                {uploadedFile && !isUploading && (
                    <div className="mt-4 p-3 bg-gray-50 rounded-md">
                      <p className="text-sm text-gray-600">
                        Selected file:{" "}
                        <span className="font-medium">{uploadedFile.name}</span>{" "}
                        ({Math.round(uploadedFile.size / 1024)} KB)
                      </p>
                    </div>
                )}

                {/* PASS FILENAME TO INVITECODE */}
                <InviteCode port={port} filename={uploadedFilename} />
              </div>
          ) : (
              <div>
                <FileDownload onDownload={handleDownload} isDownloading={isDownloading} />
              </div>
          )}
        </div>

        <footer className="mt-12 text-center text-gray-500 text-sm">
          <p>PeerLink © {new Date().getFullYear()}</p>
        </footer>
      </div>
  );
}
